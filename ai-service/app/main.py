from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import recommendations, pricing, fraud

app = FastAPI(
    title="Ecommerce AI Service",
    description="Recommendations, dynamic pricing, and fraud detection for the platform.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # tighten to the gateway's origin in production
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(recommendations.router)
app.include_router(pricing.router)
app.include_router(fraud.router)


@app.get("/health")
def health():
    return {"status": "UP"}
