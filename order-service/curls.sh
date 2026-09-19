#!/usr/bin/env bash
# Order Service — http://localhost:8081/api/v1/order

BASE_URL="http://localhost:8081/api/v1/order"

# Crear orden
curl -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "orderLineItemsList": [
      {
        "sku": "SKU-001",
        "price": 99.99,
        "quantity": 2
      },
      {
        "sku": "SKU-002",
        "price": 49.50,
        "quantity": 1
      }
    ]
  }'

# Listar órdenes
curl -X GET "$BASE_URL"

# Obtener orden por ID
curl -X GET "$BASE_URL/1"

# Eliminar orden por ID
curl -X DELETE "$BASE_URL/1"
