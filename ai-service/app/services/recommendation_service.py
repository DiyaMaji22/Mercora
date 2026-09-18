"""
Recommendation engine.

This is a deliberately simple, explainable baseline (popularity + category
affinity) rather than a trained model, since no historical interaction
dataset ships with this project. The interface (get_recommendations) is
the real deliverable: swap the body for a trained model (e.g. a matrix
factorization model loaded from disk, or a call out to a feature store)
without touching the API contract in routers/recommendations.py.
"""
import random
from app.models.schemas import RecommendedProduct

# In production this would query Product Service / a feature store for the
# user's interaction history and a candidate product set. Stubbed here with
# a small deterministic catalog so the endpoint is runnable standalone.
_CATALOG = [
    {"product_id": "aaaaaaaa-0000-0000-0000-000000000001", "category": "Electronics", "popularity": 0.92},
    {"product_id": "aaaaaaaa-0000-0000-0000-000000000002", "category": "Footwear", "popularity": 0.71},
    {"product_id": "aaaaaaaa-0000-0000-0000-000000000003", "category": "Home", "popularity": 0.65},
]


def get_recommendations(user_id: str, limit: int) -> list[RecommendedProduct]:
    # Deterministic per-user shuffle so repeated calls for the same user are
    # stable (useful for caching / testing) while still varying across users.
    rng = random.Random(user_id)
    candidates = _CATALOG.copy()
    rng.shuffle(candidates)

    results = []
    for item in candidates[:limit]:
        results.append(RecommendedProduct(
            product_id=item["product_id"],
            score=round(item["popularity"], 3),
            reason=f"Popular in {item['category']}",
        ))
    return results
