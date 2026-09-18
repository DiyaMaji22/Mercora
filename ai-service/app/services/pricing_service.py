"""
Dynamic pricing engine.

Rule-based demand pricing: scarcity (low stock) and demand velocity (views
and purchases in the last hour) push price up within a bounded range;
excess stock with low demand nudges price down to clear inventory. Bounded
to +/-15% of base price so this can't run away - a real deployment would
likely also feed this signal (not the final price) into a human/business
rule layer before it's applied to the live catalog.
"""
from app.models.schemas import DynamicPriceResponse

MAX_ADJUSTMENT_PCT = 0.15
LOW_STOCK_THRESHOLD = 10


def calculate_dynamic_price(
    product_id: str,
    base_price: float,
    current_stock: int,
    views_last_hour: int,
    purchases_last_hour: int,
) -> DynamicPriceResponse:
    adjustment = 0.0
    reasons = []

    if current_stock <= LOW_STOCK_THRESHOLD and current_stock > 0:
        scarcity_factor = (LOW_STOCK_THRESHOLD - current_stock) / LOW_STOCK_THRESHOLD
        adjustment += scarcity_factor * 0.08
        reasons.append(f"low stock ({current_stock} units remaining)")

    conversion_rate = purchases_last_hour / views_last_hour if views_last_hour > 0 else 0
    if conversion_rate > 0.15:
        adjustment += 0.05
        reasons.append(f"high demand ({conversion_rate:.0%} conversion this hour)")
    elif views_last_hour > 20 and purchases_last_hour == 0:
        adjustment -= 0.06
        reasons.append("high interest but no conversions - price may be a barrier")

    if current_stock > 100 and purchases_last_hour == 0:
        adjustment -= 0.05
        reasons.append("excess stock with no recent sales")

    adjustment = max(-MAX_ADJUSTMENT_PCT, min(MAX_ADJUSTMENT_PCT, adjustment))
    suggested_price = round(base_price * (1 + adjustment), 2)

    reason_text = "; ".join(reasons) if reasons else "no notable demand signal, price unchanged"

    return DynamicPriceResponse(
        product_id=product_id,
        base_price=base_price,
        suggested_price=suggested_price,
        adjustment_pct=round(adjustment * 100, 2),
        reason=reason_text,
    )
