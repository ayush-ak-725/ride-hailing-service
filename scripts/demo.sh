#!/usr/bin/env bash
# Walks through the main flows and edge cases against a running server.
# Usage: ./mvnw spring-boot:run   (in another terminal), then ./scripts/demo.sh [base-url]
# Requires curl and jq. Safe to re-run: phones, plates and coupon codes get a per-run suffix,
# and each run works in its own grid region so earlier runs' drivers don't interfere.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
RUN=$(( $(date +%s) % 100000 ))
X0=$(( (RUN % 40) * 40 - 800 ))   # this run's region on the grid

step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

# call METHOD PATH [JSON] -> prints "HTTP <status>" and the pretty body; body is kept in $BODY
call() {
  local method=$1 path=$2 data=${3:-}
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE_URL$path")
  [[ -n $data ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  local response; response=$(curl "${args[@]}")
  BODY=$(sed '$d' <<<"$response")
  echo "HTTP $(tail -n1 <<<"$response")"
  [[ -n $BODY ]] && jq -C . <<<"$BODY"
  return 0
}
field() { jq -r "$1" <<<"$BODY"; }
at() { printf '{"location": {"x": %s, "y": %s}}' "$1" "$2"; }

step "Register two riders"
call POST /users "{\"name\": \"Asha\", \"phone\": \"91${RUN}0001\"}";  ASHA=$(field .id)
call POST /users "{\"name\": \"Vikram\", \"phone\": \"91${RUN}0002\"}"; VIKRAM=$(field .id)

step "Register a hatchback 1 km away and a sedan 2 km away"
call POST /drivers "{\"name\": \"Ravi\", \"phone\": \"81${RUN}0001\", \"plateNumber\": \"HB-$RUN\", \"carType\": \"HATCHBACK\", \"location\": {\"x\": $((X0 + 1)), \"y\": 0}}"
HATCH=$(field .id)
call POST /drivers "{\"name\": \"Meera\", \"phone\": \"81${RUN}0002\", \"plateNumber\": \"SD-$RUN\", \"carType\": \"SEDAN\", \"location\": {\"x\": $((X0 + 2)), \"y\": 0}, \"rating\": 4.8}"
SEDAN=$(field .id)

step "Edge case: minimum fare. Asha books a hatchback; the cab ends 1 km from pickup (tiered Rs 10 < minimum Rs 50)"
call POST /rides "{\"userId\": \"$ASHA\", \"pickup\": {\"x\": $X0, \"y\": 0}, \"carType\": \"HATCHBACK\"}"; RIDE=$(field .id)
call POST "/rides/$RIDE/end" >/dev/null; jq -C '{status, fare}' <<<"$BODY"

step "Edge case: free upgrade. Hatchback drives out of range; Asha asks for a hatchback and gets the sedan"
call PUT "/drivers/$HATCH/location" "$(at $((X0 + 50)) 0)" >/dev/null
call POST /rides "{\"userId\": \"$ASHA\", \"pickup\": {\"x\": $X0, \"y\": 0}, \"carType\": \"HATCHBACK\"}" >/dev/null
RIDE=$(field .id); jq -C '{id, requestedCarType, assignedCarType, upgraded}' <<<"$BODY"

step "...a 10 km trip (pickup -> (x+6, y+8)) is billed at the hatchback rate: Rs 69, not the sedan's Rs 106"
call PUT "/drivers/$SEDAN/location" "$(at $((X0 + 6)) 8)" >/dev/null
call POST "/rides/$RIDE/end" >/dev/null; jq -C '.fare' <<<"$BODY"

step "Coupons: add SAVE10-$RUN (10% off, capped at Rs 100); Vikram books with it in lower case"
call POST /coupons "{\"code\": \"SAVE10-$RUN\", \"percentOff\": 10, \"maxDiscount\": 100}"
call POST /rides "{\"userId\": \"$VIKRAM\", \"pickup\": {\"x\": $((X0 + 6)), \"y\": 8}, \"carType\": \"SEDAN\", \"couponCode\": \"save10-$RUN\"}" >/dev/null
RIDE=$(field .id); jq -C '{id, couponCode}' <<<"$BODY"
call PUT "/drivers/$SEDAN/location" "$(at $((X0 + 6)) 18)" >/dev/null
call POST "/rides/$RIDE/end" >/dev/null; jq -C '.fare' <<<"$BODY"

step "Edge case: invalid coupon is rejected (422) and nothing is reserved"
call POST /rides "{\"userId\": \"$VIKRAM\", \"pickup\": {\"x\": $X0, \"y\": 0}, \"carType\": \"SEDAN\", \"couponCode\": \"NOPE\"}"

step "Edge case: no driver within 5 km (409)"
call POST /rides "{\"userId\": \"$VIKRAM\", \"pickup\": {\"x\": $((X0 + 25)), \"y\": 0}, \"carType\": \"SEDAN\"}"

step "Edge case: unknown car type (400 names the bad field)"
call POST /rides "{\"userId\": \"$VIKRAM\", \"pickup\": {\"x\": 0}, \"carType\": \"SUV\"}"

step "Cancellation: free within 2 minutes of booking (after that: Rs 25 hatchback / Rs 40 sedan)"
call POST /rides "{\"userId\": \"$VIKRAM\", \"pickup\": {\"x\": $((X0 + 6)), \"y\": 18}, \"carType\": \"SEDAN\"}" >/dev/null
call POST "/rides/$(field .id)/cancel" >/dev/null; jq -C '{id, status, cancellationFee}' <<<"$BODY"

step "Surge: 4 hatchbacks in a fresh area, 3 already on rides (75% busy) -> the next booking locks in 1.25x"
SX=$((X0 + 20))
for i in 1 2 3 4; do
  call POST /drivers "{\"name\": \"Surge $i\", \"phone\": \"82${RUN}000$i\", \"plateNumber\": \"SG$i-$RUN\", \"carType\": \"HATCHBACK\", \"location\": {\"x\": $SX, \"y\": 0}}" >/dev/null
done
for i in 1 2 3; do
  call POST /users "{\"name\": \"Busy $i\", \"phone\": \"92${RUN}000$i\"}" >/dev/null
  call POST /rides "{\"userId\": \"$(field .id)\", \"pickup\": {\"x\": $SX, \"y\": 0}, \"carType\": \"HATCHBACK\"}" >/dev/null
done
call POST /users "{\"name\": \"Surged\", \"phone\": \"92${RUN}0009\"}" >/dev/null
call POST /rides "{\"userId\": \"$(field .id)\", \"pickup\": {\"x\": $SX, \"y\": 0}, \"carType\": \"HATCHBACK\"}" >/dev/null
jq -C '{id, driverId, surgeMultiplier}' <<<"$BODY"
RIDE=$(field .id); DRIVER=$(field .driverId)
call PUT "/drivers/$DRIVER/location" "$(at $((SX + 6)) 8)" >/dev/null
call POST "/rides/$RIDE/end" >/dev/null; jq -C '.fare' <<<"$BODY"

step "Ride history for Asha and for the sedan driver"
call GET "/users/$ASHA/rides" >/dev/null
jq -C '{ongoing: [.ongoing[].id], completed: [.completed[] | {id, total: .fare.total}]}' <<<"$BODY"
call GET "/drivers/$SEDAN/rides" >/dev/null
jq -C '{ongoing: [.ongoing[].id], completed: [.completed[] | {id, total: .fare.total}]}' <<<"$BODY"

printf '\nSwagger UI: %s/swagger-ui.html\n' "$BASE_URL"
