import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  validMerchantId,validAuthority,validOrderId,verifiedGatewayReference,validCreditQuote
} from "../backend/supabase/functions/sura-zarinpal/payment_rules.ts";

test("merchant ID, authority and order ID are strongly validated", () => {
  assert.equal(validMerchantId("12345678-1234-1234-1234-1234567890ab"),true);
  assert.equal(validMerchantId("00000000-0000-0000-0000-000000000000"),false);
  assert.equal(validMerchantId("not-a-merchant"),false);
  assert.equal(validAuthority("A000000000000000000000000000000000"),true);
  assert.equal(validAuthority("bad/../../../"),false);
  assert.equal(validOrderId("11111111-1111-4111-8111-111111111111"),true);
  assert.equal(validOrderId("11111111-1111-4111-8111-111111111111?x"),false);
});
test("gateway accepts only cryptographically sourced verify codes 100 or 101 with valid reference", () => {
  assert.equal(verifiedGatewayReference({data:{code:100,ref_id:1234567}}),"1234567");
  assert.equal(verifiedGatewayReference({data:{code:101,ref_id:"1234567"}}),"1234567");
  for(const data of [
    {code:101,ref_id:null},{code:100,ref_id:0},{code:100,ref_id:""},
    {code:101,ref_id:"invalid"},{code:100,ref_id:-42},
    {code:101,ref_id:1.5},{code:101,ref_id:Number.MAX_SAFE_INTEGER+1},
    {code:99,ref_id:1234567},{code:-22,ref_id:1234567},
  ])assert.equal(verifiedGatewayReference({data}),null);
  assert.equal(verifiedGatewayReference({errors:[{code:100}]}),null);
  assert.equal(verifiedGatewayReference(null),null);
});
test("price and ten-percent VAT come from an exact trusted rial quote", () => {
  const quote={
    quantity:3,unit_price_rial:100000,subtotal_rial:300000,
    vat_rate_percent:10,vat_rial:30000,total_rial:330000,currency:"IRR"
  };
  assert.equal(validCreditQuote(quote,3),true);
  for(const patch of [
    {total_rial:300000},{currency:"IRT"},{quantity:4},{vat_rial:29999},
    {vat_rate_percent:9},{unit_price_rial:-100000},{subtotal_rial:300001},
    {total_rial:"330000"},{unit_price_rial:1e16}
  ])assert.equal(validCreditQuote({...quote,...patch},3),false,JSON.stringify(patch));
  assert.equal(validCreditQuote(quote,0),false);
  assert.equal(validCreditQuote(quote,999999),false);
});

test("checkout uses active ZarinPal v4 host and explicit rial currency", () => {
  const source=readFileSync(new URL("../backend/supabase/functions/sura-zarinpal/index.ts",import.meta.url),"utf8");
  assert.ok(source.includes("https://payment.zarinpal.com/pg/v4/payment/"));
  assert.ok(!source.includes('gatewayBase = "https://api.zarinpal.com'));
  assert.ok(source.includes('merchant_id:merchant,amount,currency:"IRR"'));
  assert.ok(source.includes("gateway_network_unavailable"));
  assert.ok(source.includes("gateway_http_error"));
});
