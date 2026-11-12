/** Pure, side-effect-free guards shared by the production payment endpoint and its tests. */
const object = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;

const integer = (v: unknown): v is number => typeof v === "number" && Number.isSafeInteger(v);

export function validMerchantId(value: string): boolean {
  return /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(value) &&
    !/^0{8}(?:-0{4}){3}-0{12}$/.test(value);
}

export function validAuthority(value: unknown): value is string {
  return typeof value === "string" && /^[A-Za-z0-9_-]{20,64}$/.test(value);
}

export function validOrderId(value: unknown): value is string {
  return typeof value === "string" &&
    /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(value);
}

export function verifiedGatewayReference(payload: unknown): string | null {
  const data = object(object(payload)?.data);
  if (!data || (data.code !== 100 && data.code !== 101)) return null;
  const ref = data.ref_id;
  if (typeof ref === "number" && (!integer(ref) || ref <= 0)) return null;
  if (typeof ref !== "string" && typeof ref !== "number") return null;
  const value = String(ref);
  return /^[1-9][0-9]{0,29}$/.test(value) ? value : null;
}

export function validCreditQuote(payload: unknown, quantity: number): boolean {
  const q = object(payload);
  if (!q || !integer(quantity) || quantity < 1 || quantity > 999999) return false;
  const unit = q.unit_price_rial, subtotal = q.subtotal_rial;
  const vat = q.vat_rial, total = q.total_rial;
  if (![unit,subtotal,vat,total].every(integer)) return false;
  if (!integer(unit) || !integer(subtotal) || !integer(vat) || !integer(total)) return false;
  if (unit <= 0 || subtotal <= 0 || vat < 0 || total <= 0) return false;
  if (q.quantity !== quantity || q.currency !== "IRR" || q.vat_rate_percent !== 10) return false;
  const expectedSub = unit * quantity;
  const expectedVat = Math.floor((expectedSub * 10 + 50) / 100);
  return Number.isSafeInteger(expectedSub) &&
    Number.isSafeInteger(expectedVat) &&
    Number.isSafeInteger(expectedSub + expectedVat) &&
    subtotal === expectedSub && vat === expectedVat && total === subtotal + vat;
}
