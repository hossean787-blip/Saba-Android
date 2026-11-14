import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  validMerchantId,validAuthority,validOrderId,verifiedGatewayReference,validCreditQuote,acceptedGatewayAuthority,recoverableOrderId
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

test("checkout never accepts a non-success gateway authority or spoofed authority", () => {
  const authority="A000000000000000000000000000000000";
  assert.equal(acceptedGatewayAuthority({data:{code:100,authority}}),authority);
  for(const payload of [
    null,{}, {data:null}, {data:{code:101,authority}},
    {data:{code:100,authority:"bad/../../../"}},
    {data:{code:100,authority:999}}, {data:{code:0,authority}},
    {errors:{code:100},data:{authority}},
  ]) assert.equal(acceptedGatewayAuthority(payload),null);
});

test("gateway errors and malformed checkout responses close unissued orders", () => {
  const source=readFileSync(new URL("../backend/supabase/functions/sura-zarinpal/index.ts",import.meta.url),"utf8");
  assert.ok(source.includes("async function failCreatedOrder("));
  assert.ok(source.includes("&state=eq.created&authority=is.null"));
  assert.ok(source.includes('state:"failed"'));
  assert.equal(source.split("await failCreatedOrder(order.id);").length-1,2);
});

test("payment recovery returns only an issued unsettled order ID", () => {
  const id="11111111-1111-4111-8111-111111111111";
  const authority="A000000000000000000000000000000000";
  assert.equal(recoverableOrderId({id,state:"requested",authority}),id);
  for(const row of [
    null,{}, {id,state:"created",authority}, {id,state:"failed",authority},
    {id,state:"verified",authority}, {id,state:"requested",authority:null},
    {id,state:"requested",authority:"bad"}, {id:"../bad",state:"requested",authority}
  ]) assert.equal(recoverableOrderId(row),null);
});

test("payment recovery route is authorized and account-scoped", () => {
  const source=readFileSync(new URL("../backend/supabase/functions/sura-zarinpal/index.ts",import.meta.url),"utf8");
  assert.ok(source.includes('if(route==="pending")'));
  assert.ok(source.includes('getLatestRecoverableOrder(user.id)'));
  assert.ok(source.includes('"&owner_user_id=eq."+encodeURIComponent(userId)'));
  assert.ok(source.includes('"&state=eq.requested&authority=not.is.null"'));
});

test("public sales use a server-side activation gate and static egress", () => {
  const payment=readFileSync(new URL("../backend/supabase/functions/sura-zarinpal/index.ts",import.meta.url),"utf8");
  const quote=readFileSync(new URL("../backend/supabase/functions/sura-credit-quote/index.ts",import.meta.url),"utf8");
  for (const source of [payment,quote]) {
    assert.ok(source.includes('SURA_PAYMENTS_LIVE_ENABLED'));
    assert.ok(source.includes('SURA_PAYMENT_EGRESS_URL'));
    assert.ok(source.includes('SURA_PAYMENT_EGRESS_SECRET'));
  }
  assert.ok(payment.includes('if(!checkoutReady)return reply({error:"checkout_not_enabled"},503);'));
  assert.ok(quote.includes("payment_enabled:enabled"));
});
