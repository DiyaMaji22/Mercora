-- release_hold.lua
-- Releases a checkout hold. Two modes controlled by ARGV[2]:
--   "RETURN"  -> hold expired/cancelled: quantity is returned to live stock
--   "CONSUME" -> order confirmed: hold is simply cleared, stock stays deducted
--
-- KEYS[1] = hold:user:{userId}:product:{productId}
-- KEYS[2] = stock:product:{productId}
--
-- ARGV[1] = expected quantity (defensive check; 0 to skip check)
-- ARGV[2] = mode: "RETURN" or "CONSUME"
--
-- Returns:
--   1  if hold existed and was processed
--   0  if hold did not exist (already released/expired - no-op, safe to ignore)

local holdKey = KEYS[1]
local stockKey = KEYS[2]

local expectedQty = tonumber(ARGV[1])
local mode = ARGV[2]

local heldQty = redis.call('GET', holdKey)
if not heldQty then
    return 0
end

heldQty = tonumber(heldQty)

if expectedQty > 0 and heldQty ~= expectedQty then
    -- defensive: quantity mismatch, do not silently release the wrong amount
    return redis.error_reply('HOLD_QTY_MISMATCH')
end

redis.call('DEL', holdKey)

if mode == 'RETURN' then
    redis.call('INCRBY', stockKey, heldQty)
end

return 1
