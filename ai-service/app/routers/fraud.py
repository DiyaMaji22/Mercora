from fastapi import APIRouter
from app.models.schemas import FraudCheckRequest, FraudCheckResponse
from app.services import fraud_service

router = APIRouter(prefix="/fraud-check", tags=["fraud"])


@router.post("", response_model=FraudCheckResponse)
def check_order(request: FraudCheckRequest) -> FraudCheckResponse:
    return fraud_service.evaluate_order(request)
