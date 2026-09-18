-- hold_stock.lua
-- Atomically checks available stock and places a time-limited checkout hold.
--
-- KEYS[1] = stock:product:{productId}          (integer, live available stock)
-- KEYS[2] = hold:user:{userId}:product:{productId}  (hold record for this user/product)
--
-- ARGV[1] = quantity requested
-- ARGV[2] = hold TTL in seconds (e.g. 300 for flash sale checkout hold)
--
-- Returns:
--   {1, remainingStock}  on success
--   {0, currentStock}    if insufficient stock
--   {-1, 0}              if a hold already exists for this user/product (idempotency guard)

local stockKey = KEYS[1]
local holdKey = KEYS[2]

local qty = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

if redis.call('EXISTS', holdKey) == 1 then
    return {-1, 0}
end

local currentStock = tonumber(redis.call('GET', stockKey) or '0')

if currentStock < qty then
    return {0, currentStock}
end

-- Atomic deduct + hold creation. Both operations happen within this single
-- Lua execution, so no other client can observe an intermediate state
-- (Redis executes scripts single-threaded).
local newStock = redis.call('DECRBY', stockKey, qty)
redis.call('SET', holdKey, qty, 'EX', ttl)

return {1, newStock}
