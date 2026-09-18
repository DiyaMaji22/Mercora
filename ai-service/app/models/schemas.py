from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


class RecommendationRequest(BaseModel):
    user_id: str
    limit: int = Field(default=10, ge=1, le=50)


class RecommendedProduct(BaseModel):
    product_id: str
    score: float
    reason: str


class RecommendationResponse(BaseModel):
    user_id: str
    recommendations: List[RecommendedProduct]


class DynamicPriceRequest(BaseModel):
    product_id: str
    base_price: float
    current_stock: int
    views_last_hour: int = 0
    purchases_last_hour: int = 0


class DynamicPriceResponse(BaseModel):
    product_id: str
    base_price: float
    suggested_price: float
    adjustment_pct: float
    reason: str


class FraudCheckRequest(BaseModel):
    order_id: str
    user_id: str
    amount: float
    item_count: int
    account_age_days: int
    shipping_country: Optional[str] = None
    billing_country: Optional[str] = None
    orders_last_24h: int = 0


class FraudCheckResponse(BaseModel):
    order_id: str
    risk_score: float  # 0.0 (low risk) - 1.0 (high risk)
    risk_level: str    # LOW | MEDIUM | HIGH
    signals: List[str]
    recommendation: str  # APPROVE | REVIEW | DECLINE
    evaluated_at: datetime
