#!/bin/bash
while ! docker exec ecommerce-postgres pg_isready -U ecommerce; do
  sleep 2
done
docker exec -i ecommerce-postgres psql -U ecommerce -d ecommerce < backend/inventory-service/src/main/resources/db/seed/seed.sql
echo "Database seeded!"
