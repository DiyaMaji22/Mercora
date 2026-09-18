from fastapi import APIRouter
from app.models.schemas import RecommendationRequest, RecommendationResponse
from app.services import recommendation_service

router = APIRouter(prefix="/recommendations", tags=["recommendations"])


@router.post("", response_model=RecommendationResponse)
def get_recommendations(request: RecommendationRequest) -> RecommendationResponse:
    recs = recommendation_service.get_recommendations(request.user_id, request.limit)
    return RecommendationResponse(user_id=request.user_id, recommendations=recs)
