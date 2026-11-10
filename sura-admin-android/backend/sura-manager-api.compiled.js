// source-sha256:cb9d3b396f5081fc7e751785e4823c5ba858c03715dc513bd4b44ed0a9e13b39
/** SURA Manager API v1. Privileged operations happen ONLY here, never in APK. */
const BASE = Deno.env.get('SUPABASE_URL') || '';
const ANON = Deno.env.get('SUPABASE_ANON_KEY') || '';
const SERVICE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') || '';
const ORIGIN = 'https://appassets.androidplatform.net';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ROLES = ['admin', 'editor', 'viewer'];
const CORS = { 'access-control-allow-origin': ORIGIN, 'vary': 'Origin', 'access-control-allow-methods': 'POST,OPTIONS', 'access-control-allow-headers': 'authorization,apikey,content-type', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' };
const HEADERS = { apikey: SERVICE, authorization: `Bearer ${SERVICE}` };
const send = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { ...CORS, 'content-type': 'application/json; charset=utf-8' } });
const validateInt = (n, min, max) => typeof n === 'number' && Number.isSafeInteger(n) && n >= min && n <= max;
const validId = (v) => typeof v === 'string' && UUID.test(v);
const object = (v) => !!v && typeof v === 'object' && !Array.isArray(v);
const reasonValid = (v) => typeof v === 'string' && v.trim().length >= 5 && v.trim().length <= 500;
const params = (p) => new URLSearchParams(Object.entries(p).map(([k, v]) => [k, String(v)]));
async function request(url, headers = HEADERS) {
    return fetch(url, { headers, signal: AbortSignal.timeout(12000) });
}
async function jsonFrom(r) { return r.json().catch(() => null); }
async function identity(req) {
    const bearer = req.headers.get('authorization') || '';
    if (!/^Bearer \S+$/.test(bearer))
        return null;
    const auth = await request(`${BASE}/auth/v1/user`, { apikey: ANON, authorization: bearer }).catch(() => null);
    if (!auth?.ok)
        return null;
    const u = await jsonFrom(auth);
    if (!validId(u?.id) || !u?.email_confirmed_at)
        return null;
    const roles = await request(`${BASE}/rest/v1/sura_admin_roles?user_id=eq.${u.id}&select=role&limit=1`).catch(() => null);
    if (!roles?.ok)
        return null;
    const found = (await jsonFrom(roles))?.[0]?.role;
    if (!ROLES.includes(found))
        return null;
    return { id: u.id, email: String(u.email || ''), role: found };
}
async function rest(table, filters, count = false) {
    const url = `${BASE}/rest/v1/${table}?${params(filters)}`;
    const r = await fetch(url, { headers: { ...HEADERS, ...(count ? { 'Prefer': 'count=exact' } : {}) }, signal: AbortSignal.timeout(16000) });
    if (!r.ok)
        throw new Error('database_read_failed');
    const rows = await jsonFrom(r);
    if (!Array.isArray(rows))
        throw new Error('invalid_db_response');
    const total = count ? Number((r.headers.get('content-range') || '').split('/')[1]) : undefined;
    return { rows, total: Number.isSafeInteger(total) ? total : null };
}
async function invoke(name, payload) {
    const r = await fetch(`${BASE}/rest/v1/rpc/${name}`, {
        method: 'POST', headers: { ...HEADERS, 'content-type': 'application/json' }, body: JSON.stringify(payload), signal: AbortSignal.timeout(16000)
    });
    if (!r.ok)
        throw new Error('database_write_failed');
    return jsonFrom(r);
}
const validDay = (v) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(v + 'T00:00:00Z')) && new Date(v + 'T00:00:00Z').toISOString().slice(0, 10) === v;
async function authUser(id) {
    const response = await request(`${BASE}/auth/v1/admin/users/${encodeURIComponent(id)}`);
    if (response.status === 404)
        return null;
    if (!response.ok)
        throw new Error('users_unavailable');
    const payload = await jsonFrom(response);
    return payload?.user || payload;
}
function safeUser(u) {
    if (!validId(u?.id))
        return null;
    return { id: u.id, email: String(u.email || ''), verified: !!u.email_confirmed_at, created_at: u.created_at || null, last_sign_in_at: u.last_sign_in_at || null, banned_until: u.banned_until || null };
}
async function mapLimited(list, limit, fn) {
    const output = [];
    for (let start = 0; start < list.length; start += limit) {
        const part = await Promise.all(list.slice(start, start + limit).map(fn));
        output.push(...part);
    }
    return output;
}
async function rolesMap() {
    const r = await rest('sura_admin_roles', { select: 'user_id,role', limit: 1000 });
    return new Map(r.rows.map((row) => [row.user_id, row.role]));
}
function result(raw) {
    if (object(raw) && raw.error) {
        return send(raw, 409);
    }
    return send(raw);
}
async function handler(req) {
    if (req.method === 'OPTIONS')
        return new Response(null, { status: 204, headers: CORS });
    if (req.method !== 'POST')
        return send({ error: 'method_not_allowed' }, 405);
    if (!BASE || !ANON || !SERVICE)
        return send({ error: 'server_unconfigured' }, 503);
    const user = await identity(req);
    if (!user)
        return send({ error: 'unauthorized' }, 401);
    const end = new URL(req.url).pathname.split('/').filter(Boolean).pop();
    const body = await req.json().catch(() => null);
    if (!object(body))
        return send({ error: 'invalid_request' }, 400);
    const admin = user.role === 'admin';
    const staff = admin || user.role === 'editor';
    if (end === 'summary') {
        const summary = await invoke('sura_manager_summary', {});
        return send({ summary });
    }
    if (end === 'users') {
        if (!staff)
            return send({ error: 'insufficient_role' }, 403);
        const page = body.page ?? 1, perPage = body.perPage ?? 30;
        if (!validateInt(page, 1, 10000) || !validateInt(perPage, 1, 50))
            return send({ error: 'invalid_pagination' }, 400);
        const q = params({ page, per_page: perPage });
        const response = await request(`${BASE}/auth/v1/admin/users?${q}`);
        if (!response.ok)
            throw new Error('users_unavailable');
        const result = await jsonFrom(response);
        const rows = (Array.isArray(result?.users) ? result.users : []).map(safeUser).filter(Boolean);
        const roles = await rolesMap();
        return send({ page, perPage, users: rows.map((u) => ({ ...u, role: roles.get(u.id) || null })), nextPage: rows.length === perPage ? page + 1 : null });
    }
    if (end === 'user') {
        if (!staff)
            return send({ error: 'insufficient_role' }, 403);
        if (!validId(body.id))
            return send({ error: 'invalid_user_id' }, 400);
        const u = safeUser(await authUser(body.id));
        if (!u)
            return send({ error: 'user_not_found' }, 404);
        const [roles, owner, tax, orders] = await Promise.all([
            rolesMap(),
            rest('sura_owner_wallets', { owner_id: `eq.${body.id}`, select: 'credits,updated_at', limit: 1 }),
            rest('sura_taxpayer_wallets', { owner_id: `eq.${body.id}`, select: 'local_taxpayer_id,credits,updated_at', order: 'local_taxpayer_id.asc', limit: 100 }),
            rest('sura_payment_orders', { owner_user_id: `eq.${body.id}`, select: 'id,state,amount_rial,credit_units,created_at,verified_at,ref_id', order: 'created_at.desc', limit: 30 })
        ]);
        return send({ user: { ...u, role: roles.get(u.id) || null }, wallet: owner.rows[0] || null, taxpayers: tax.rows, orders: orders.rows, taxpayers_truncated: tax.rows.length === 100 });
    }
    if (end === 'orders') {
        const page = body.page ?? 1, perPage = body.perPage ?? 40;
        if (!validateInt(page, 1, 10000) || !validateInt(perPage, 1, 100))
            return send({ error: 'invalid_pagination' }, 400);
        const f = { select: 'id,owner_user_id,state,amount_rial,credit_units,authority,ref_id,created_at,verified_at', order: 'created_at.desc', limit: perPage, offset: (page - 1) * perPage };
        if (body.state !== undefined) {
            if (!['created', 'requested', 'verified', 'failed'].includes(body.state))
                return send({ error: 'invalid_state' }, 400);
            f.state = `eq.${body.state}`;
        }
        if (body.owner !== undefined) {
            if (!validId(body.owner))
                return send({ error: 'invalid_owner' }, 400);
            f.owner_user_id = `eq.${body.owner}`;
        }
        if (body.from !== undefined) {
            if (!validDay(body.from))
                return send({ error: 'invalid_date' }, 400);
            f.created_at = `gte.${body.from}T00:00:00Z`;
        }
        // Multiple filtering by same column needs AND; for date-end use 'and' syntax only when from is also present.
        if (body.to !== undefined) {
            if (!validDay(body.to))
                return send({ error: 'invalid_date' }, 400);
            const endDate = new Date(Date.parse(body.to + 'T00:00:00Z') + 86400000).toISOString();
            if (f.created_at) {
                delete f.created_at;
                f.and = `(created_at.gte.${body.from}T00:00:00Z,created_at.lt.${endDate})`;
            }
            else
                f.created_at = `lt.${endDate}`;
        }
        const orders = await rest('sura_payment_orders', f, true);
        return send({ page, perPage, total: orders.total, orders: orders.rows });
    }
    if (end === 'health') {
        const [price, wallet] = await Promise.all([
            rest('sura_credit_pricing', { select: 'price_per_invoice_rial,currency,updated_at', limit: 1 }),
            rest('sura_owner_wallets', { select: 'owner_id', limit: 1 })
        ]);
        return send({ status: 'ok', service: 'sura-manager-api', database: 'ok', authentication: 'ok', server_time: new Date().toISOString(), price: price.rows[0] || null, role: user.role });
    }
    if (end === 'role-list') {
        if (!admin)
            return send({ error: 'insufficient_role' }, 403);
        const [roles, history] = await Promise.all([
            rest('sura_admin_roles', { select: 'user_id,role,updated_at', order: 'updated_at.desc', limit: 100 }),
            rest('sura_manager_role_audit', { select: 'request_id,actor_id,target_id,role_before,role_after,reason,created_at', order: 'created_at.desc', limit: 100 })
        ]);
        const current = await mapLimited(roles.rows, 8, async (r) => {
            const u = safeUser(await authUser(r.user_id));
            return { ...r, email: u?.email || '', verified: u?.verified ?? false };
        });
        return send({ roles: current, history: history.rows, role_list_truncated: roles.rows.length === 100 });
    }
    if (end === 'role-assign') {
        if (!admin)
            return send({ error: 'insufficient_role' }, 403);
        const target = body.target, role = body.role, expected = body.expected ?? null, reason = body.reason, nonce = body.request;
        if (!validId(target) || !validId(nonce) || !['admin', 'editor', 'viewer', 'none'].includes(String(role)) ||
            (expected !== null && !ROLES.includes(String(expected))) || !reasonValid(reason))
            return send({ error: 'invalid_role_request' }, 400);
        const auth = safeUser(await authUser(target));
        if (!auth || !auth.verified)
            return send({ error: 'unverified_or_missing_user' }, 404);
        if (typeof body.confirmEmail !== 'string' || body.confirmEmail.trim().toLowerCase() !== auth.email.trim().toLowerCase())
            return send({ error: 'email_target_mismatch' }, 409);
        return result(await invoke('sura_manager_assign_role', { p_actor: user.id, p_target: target, p_role: role, p_expected: expected, p_reason: reason.trim(), p_request: nonce }));
    }
    if (end === 'price-history') {
        if (!admin)
            return send({ error: 'insufficient_role' }, 403);
        const history = await rest('sura_manager_price_audit', { select: 'request_id,actor_id,old_price_rial,new_price_rial,reason,created_at', order: 'created_at.desc', limit: 100 });
        return send({ history: history.rows });
    }
    if (end === 'price-update') {
        if (!admin)
            return send({ error: 'insufficient_role' }, 403);
        if (!validateInt(body.expected, 1000, 1000000000) || !validateInt(body.price, 1000, 1000000000) || !validId(body.request) || !reasonValid(body.reason))
            return send({ error: 'invalid_price_request' }, 400);
        return result(await invoke('sura_manager_set_price', { p_actor: user.id, p_expected: body.expected, p_new: body.price, p_reason: body.reason.trim(), p_request: body.request }));
    }
    if (end === 'audit') {
        if (!admin)
            return send({ error: 'insufficient_role' }, 403);
        const kind = body.kind;
        const choices = {
            credit: { table: 'sura_admin_credit_adjustments', fields: 'request_id,actor_user_id,owner_id,local_taxpayer_id,before_credits,after_credits,delta,reason,created_at' },
            price: { table: 'sura_manager_price_audit', fields: 'request_id,actor_id,old_price_rial,new_price_rial,reason,created_at' },
            role: { table: 'sura_manager_role_audit', fields: 'request_id,actor_id,target_id,role_before,role_after,reason,created_at' }
        };
        if (typeof kind !== 'string' || !choices[kind])
            return send({ error: 'invalid_audit_kind' }, 400);
        const page = body.page ?? 1;
        if (!validateInt(page, 1, 10000))
            return send({ error: 'invalid_pagination' }, 400);
        const c = choices[kind];
        const audit = await rest(c.table, { select: c.fields, order: 'created_at.desc', limit: 50, offset: (page - 1) * 50 }, true);
        return send({ kind, page, total: audit.total, rows: audit.rows });
    }
    return send({ error: 'not_found' }, 404);
}
Deno.serve(async (req) => {
    try {
        return await handler(req);
    }
    catch (_e) {
        return send({ error: 'service_error' }, 502);
    }
});
