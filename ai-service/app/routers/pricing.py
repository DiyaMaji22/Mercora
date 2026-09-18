from fastapi import APIRouter
from app.models.schemas import DynamicPriceRequest, DynamicPriceResponse
from app.services import pricing_service

router = APIRouter(prefix="/dynamic-price", tags=["pricing"])


@router.post("", response_model=DynamicPriceResponse)
def get_dynamic_price(request: DynamicPriceRequest) -> DynamicPriceResponse:
    return pricing_service.calculate_dynamic_price(
        product_id=request.product_id,
        base_price=request.base_price,
        current_stock=request.current_stock,
        views_last_hour=request.views_last_hour,
        purchases_last_hour=request.purchases_last_hour,
    )
