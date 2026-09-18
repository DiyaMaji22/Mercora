"""
Fraud/risk scoring engine.

Rule-based weighted signal scoring rather than a trained classifier, for
the same reason as recommendation_service.py: no labeled fraud dataset
ships with this project. Each signal below is independently interpretable,
which matters for a fraud system specifically - operators need to see WHY
an order was flagged, not just a black-box score. Swap in a trained model
by keeping evaluate_order's signature and replacing the body; keep
returning per-signal explanations either way.
"""
from datetime import datetime, timezone
from app.models.schemas import FraudCheckRequest, FraudCheckResponse

HIGH_VALUE_THRESHOLD = 500.0
VELOCITY_THRESHOLD = 3


def evaluate_order(request: FraudCheckRequest) -> FraudCheckResponse:
    score = 0.0
    signals: list[str] = []

    if request.account_age_days < 1:
        score += 0.35
        signals.append("account created within the last 24 hours")
    elif request.account_age_days < 7:
        score += 0.15
        signals.append("account less than 7 days old")

    if request.amount > HIGH_VALUE_THRESHOLD:
        score += 0.20
        signals.append(f"high order value (${request.amount:.2f})")

    if request.orders_last_24h >= VELOCITY_THRESHOLD:
        score += 0.25
        signals.append(f"{request.orders_last_24h} orders placed in the last 24 hours")

    if (
        request.shipping_country
        and request.billing_country
        and request.shipping_country != request.billing_country
    ):
        score += 0.15
        signals.append(
            f"shipping country ({request.shipping_country}) differs from "
            f"billing country ({request.billing_country})"
        )

    if request.item_count >= 10:
        score += 0.10
        signals.append(f"unusually large item count ({request.item_count})")

    score = min(1.0, round(score, 3))

    if score >= 0.6:
        risk_level, recommendation = "HIGH", "DECLINE"
    elif score >= 0.3:
        risk_level, recommendation = "MEDIUM", "REVIEW"
    else:
        risk_level, recommendation = "LOW", "APPROVE"

    if not signals:
        signals.append("no risk signals detected")

    return FraudCheckResponse(
        order_id=request.order_id,
        risk_score=score,
        risk_level=risk_level,
        signals=signals,
        recommendation=recommendation,
        evaluated_at=datetime.now(timezone.utc),
    )
