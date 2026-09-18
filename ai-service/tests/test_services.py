from app.services import fraud_service, pricing_service
from app.models.schemas import FraudCheckRequest


def test_fraud_check_flags_new_account_high_value_order():
    request = FraudCheckRequest(
        order_id="order-1",
        user_id="user-1",
        amount=750.0,
        item_count=2,
        account_age_days=0,
        orders_last_24h=1,
    )
    result = fraud_service.evaluate_order(request)

    assert result.risk_level in ("MEDIUM", "HIGH")
    assert any("account created" in s for s in result.signals)
    assert any("high order value" in s for s in result.signals)


def test_fraud_check_low_risk_order_approves():
    request = FraudCheckRequest(
        order_id="order-2",
        user_id="user-2",
        amount=25.0,
        item_count=1,
        account_age_days=400,
        orders_last_24h=1,
    )
    result = fraud_service.evaluate_order(request)

    assert result.risk_level == "LOW"
    assert result.recommendation == "APPROVE"


def test_dynamic_price_increases_for_low_stock_high_demand():
    result = pricing_service.calculate_dynamic_price(
        product_id="p1", base_price=100.0, current_stock=3,
        views_last_hour=50, purchases_last_hour=10,
    )
    assert result.suggested_price > result.base_price


def test_dynamic_price_never_exceeds_bound():
    result = pricing_service.calculate_dynamic_price(
        product_id="p1", base_price=100.0, current_stock=1,
        views_last_hour=1000, purchases_last_hour=500,
    )
    max_price = 100.0 * (1 + pricing_service.MAX_ADJUSTMENT_PCT)
    assert result.suggested_price <= max_price
