-- bulk_lock.lua
-- Places a long-lived (default 7200s) bulk-order lock, separate from normal
-- checkout holds, so bulk buyers awaiting admin approval don't compete with
-- flash-sale checkout traffic on the same stock counter.
--
-- KEYS[1] = stock:product:{productId}
-- KEYS[2] = bulk:lock:{productId}:{userId}
--
-- ARGV[1] = quantity requested
-- ARGV[2] = lock TTL in seconds (e.g. 7200)
--
-- Returns:
--   {1, remainingStock}  on success
--   {0, currentStock}    if insufficient stock
--   {-1, existingQty}    if a bulk lock already exists for this user/product

local stockKey = KEYS[1]
local lockKey = KEYS[2]

local qty = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local existing = redis.call('GET', lockKey)
if existing then
    return {-1, tonumber(existing)}
end

local currentStock = tonumber(redis.call('GET', stockKey) or '0')

if currentStock < qty then
    return {0, currentStock}
end

local newStock = redis.call('DECRBY', stockKey, qty)
redis.call('SET', lockKey, qty, 'EX', ttl)

return {1, newStock}
