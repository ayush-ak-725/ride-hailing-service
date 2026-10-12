#!/usr/bin/env bash
# Black-box check of every endpoint against the assignment's requirements: success and failure paths.
# Usage: ./scripts/api-check.sh [base-url] [alt-base-url]
#   base-url      server running the default config (default http://localhost:8080)
#   alt-base-url  optional server started with:
#                   --pricing.cancellation.grace-period=0s --ride.matching-strategy=highest-rated
#                 to check the post-grace cancellation fee and the highest-rated strategy.
# Requires curl and jq. Exits non-zero if any check fails.
set -uo pipefail

BASE="${1:-http://localhost:8080}"
ALT="${2:-}"
RUN=$(( $(date +%s) % 100000 ))
REGION=-950
SEQ_FILE=$(mktemp); echo 0 >"$SEQ_FILE"; trap 'rm -f "$SEQ_FILE"' EXIT
PASS=0
FAIL=0

next_region() { REGION=$(( REGION + 30 )); }             # each scenario gets its own grid area
# 9 unique digits per call. The counter lives in a file because helpers run inside $(...) subshells.
uniq() { local n=$(( $(cat "$SEQ_FILE") + 1 )); echo "$n" >"$SEQ_FILE"; printf '%05d%04d' "$RUN" "$n"; }

# req METHOD PATH [JSON]  -> sets STATUS and BODY
req() {
  local method=$1 path=$2 data=${3:-} response
  local args=(-s -w '\n%{http_code}' -X "$method" "$URL$path")
  [[ -n $data ]] && args+=(-H 'Content-Type: application/json' --data-raw "$data")
  response=$(curl "${args[@]}")
  BODY=$(sed '$d' <<<"$response")
  STATUS=$(tail -n1 <<<"$response")
}

# check REQ "description" EXPECTED_STATUS [jq-expression expected-output]...
check() {
  local req_id=$1 desc=$2 want=$3 ok=1 why="" got
  shift 3
  [[ $STATUS == "$want" ]] || { ok=0; why="got HTTP $STATUS"; }
  while (( $# >= 2 )); do
    got=$(jq -r "$1" <<<"$BODY" 2>/dev/null)
    [[ $got == "$2" ]] || { ok=0; why+=" [$1 -> $got, want $2]"; }
    shift 2
  done
  if (( ok )); then
    PASS=$(( PASS + 1 )); printf '  \033[32mPASS\033[0m %-4s %-68s %s\n' "$req_id" "$desc" "$want"
  else
    FAIL=$(( FAIL + 1 )); printf '  \033[31mFAIL\033[0m %-4s %-68s %s  %s\n' "$req_id" "$desc" "$want" "$why"
    printf '       body: %s\n' "$BODY"
  fi
}

section() { printf '\n\033[1m%s\033[0m\n' "$*"; }
user() { req POST /users "{\"name\": \"Rider\", \"phone\": \"9$(uniq)\"}"; jq -r .id <<<"$BODY"; }
driver() {  # driver TYPE X Y [RATING]
  local rating=${4:+, \"rating\": $4}
  req POST /drivers "{\"name\": \"Driver\", \"phone\": \"8$(uniq)\", \"plateNumber\": \"P$(uniq)\", \"carType\": \"$1\", \"location\": {\"x\": $2, \"y\": $3}$rating}"
  jq -r .id <<<"$BODY"
}
book() {    # book USER X Y TYPE [COUPON]
  local coupon=${5:+, \"couponCode\": \"$5\"}
  req POST /rides "{\"userId\": \"$1\", \"pickup\": {\"x\": $2, \"y\": $3}, \"carType\": \"$4\"$coupon}"
}
move() { req PUT "/drivers/$1/location" "{\"location\": {\"x\": $2, \"y\": $3}}"; }

URL=$BASE
printf 'Checking %s\n' "$URL"

section "R1  Register a user"
P="9$(uniq)"
req POST /users "{\"name\": \"Asha\", \"phone\": \"$P\"}"
check R1 "valid user is created" 201 '.id | startswith("user-")' true '.name' Asha
req POST /users "{\"name\": \"Copy\", \"phone\": \"$P\"}"
check R1 "duplicate phone is rejected" 409 '.error.code' DUPLICATE_USER
req POST /users '{"name": "", "phone": "12"}'
check R1 "blank name + bad phone -> one detail per field" 400 '.error.code' VALIDATION_FAILED '.error.details | length' 2
req POST /users '{"name": "Asha", '
check R1 "malformed JSON" 400 '.error.code' MALFORMED_REQUEST

section "R2  Register a driver"
next_region
PLATE="KA$(uniq)"
req POST /drivers "{\"name\": \"Ravi\", \"phone\": \"8$(uniq)\", \"plateNumber\": \"$PLATE\", \"carType\": \"SEDAN\", \"location\": {\"x\": $REGION, \"y\": 0}}"
check R2 "valid driver is created, AVAILABLE, default rating 5" 201 '.status' AVAILABLE '.rating == 5' true '.cab.carType' SEDAN
req POST /drivers "{\"name\": \"Copy\", \"phone\": \"8$(uniq)\", \"plateNumber\": \"$(tr 'A-Z' 'a-z' <<<"$PLATE")\", \"carType\": \"SEDAN\", \"location\": {\"x\": 0, \"y\": 0}}"
check R2 "duplicate plate (different case) is rejected" 409 '.error.code' DUPLICATE_CAB
req POST /drivers '{"name": "X", "phone": "8000000000", "plateNumber": "Z1", "carType": "SUV", "location": {"x": 0, "y": 0}}'
check R2 "unsupported car type names the field" 400 '.error.code' MALFORMED_REQUEST '.error.details[0]' 'carType: invalid value'
req POST /drivers '{"name": "X", "phone": "8000000000", "plateNumber": "Z1", "carType": "SEDAN", "rating": 6}'
check R2 "missing location + rating > 5" 400 '.error.code' VALIDATION_FAILED '.error.details | length' 2

section "R5  Update the location of a cab"
next_region
D=$(driver HATCHBACK $REGION 0)
move "$D" $(( REGION + 3 )) 4
check R5 "location is updated" 200 ".cab.location.x == $(( REGION + 3 ))" true '.cab.location.y == 4' true
move driver-999999 0 0
check R5 "unknown driver" 404 '.error.code' DRIVER_NOT_FOUND
req PUT "/drivers/$D/location" '{"location": {"x": 1e999, "y": 0}}'
check R5 "non-finite coordinate is rejected" 400 '.error.code' VALIDATION_FAILED
req PUT "/drivers/$D/location" '{"location": {"x": 1}}'
check R5 "missing coordinate is rejected" 400 '.error.details[0]' 'location.y: must not be null'

section "R3  Book a ride if a driver is available within the radius (5 km)"
next_region
FAR=$(driver HATCHBACK $(( REGION + 4 )) 0)
NEAR=$(driver HATCHBACK $(( REGION + 1 )) 0)
U=$(user)
book "$U" $REGION 0 HATCHBACK
check R3 "books the nearest available driver" 201 '.driverId' "$NEAR" '.status' ONGOING '.upgraded' false
RIDE_NEAR=$(jq -r .id <<<"$BODY")
book "$U" $REGION 0 HATCHBACK
check R3 "same user cannot hold two ongoing rides" 409 '.error.code' USER_HAS_ONGOING_RIDE
book "$(user)" $REGION 0 HATCHBACK
check R3 "busy driver is skipped, next nearest assigned" 201 '.driverId' "$FAR"
book "$(user)" $REGION 0 HATCHBACK
check R3 "all nearby drivers busy -> no driver" 409 '.error.code' NO_DRIVER_AVAILABLE
next_region
EDGE=$(driver SEDAN $(( REGION + 3 )) 4)
book "$(user)" $REGION 0 SEDAN
check R3 "driver exactly 5 km away is within radius" 201 '.driverId' "$EDGE"
next_region
driver SEDAN $(( REGION + 5 )) 1 >/dev/null   # ~5.10 km
book "$(user)" $REGION 0 SEDAN
check R3 "driver just outside 5 km -> no driver" 409 '.error.code' NO_DRIVER_AVAILABLE
book user-999999 $REGION 0 SEDAN
check R3 "unknown user" 404 '.error.code' USER_NOT_FOUND
req POST /rides "{\"userId\": \"$U\", \"carType\": \"SEDAN\"}"
check R3 "missing pickup" 400 '.error.details[0]' 'pickup: must not be null'

section "R9  Car types: Hatchback -> Sedan free upgrade"
next_region
SED=$(driver SEDAN $REGION 0)
book "$(user)" $REGION 0 HATCHBACK
check R9 "no hatchback -> sedan assigned, flagged upgraded" 201 '.assignedCarType' SEDAN '.requestedCarType' HATCHBACK '.upgraded' true
UPG=$(jq -r .id <<<"$BODY")
move "$SED" $(( REGION + 6 )) 8
req POST "/rides/$UPG/end"
check R9 "upgraded 10 km ride billed at hatchback rate (69, not 106)" 200 '.fare.total == 69' true
next_region
HB_NEAR_SEDAN=$(driver HATCHBACK $(( REGION + 4 )) 0)
driver SEDAN $(( REGION + 1 )) 0 >/dev/null
book "$(user)" $REGION 0 HATCHBACK
check R9 "hatchback available -> no upgrade even if sedan is closer" 201 '.driverId' "$HB_NEAR_SEDAN" '.upgraded' false
next_region
driver HATCHBACK $REGION 0 >/dev/null
book "$(user)" $REGION 0 SEDAN
check R9 "sedan requested, only hatchback -> never downgraded" 409 '.error.code' NO_DRIVER_AVAILABLE

section "R4 + R6  End a ride and get its cost (tiered pricing + minimum fare)"
# end_after TYPE DX DY -> books at region origin, moves the cab by (DX, DY), ends the ride
end_after() {
  next_region
  local d; d=$(driver "$1" $REGION 0)
  book "$(user)" $REGION 0 "$1"; local r; r=$(jq -r .id <<<"$BODY")
  move "$d" "$(awk "BEGIN{print $REGION + $2}")" "$3"
  req POST "/rides/$r/end"
}
end_after HATCHBACK 0 0
check R6 "hatchback 0 km -> minimum fare 50" 200 '.fare.total == 50' true '.status' COMPLETED
end_after HATCHBACK 2.5 0
check R6 "hatchback 2.5 km -> tiered 24, floored to 50" 200 '.fare.baseFare == 50' true
end_after HATCHBACK 7 0
check R6 "hatchback 7 km -> 2*10 + 3*8 + 2*5 = 54" 200 '.fare.total == 54' true
end_after HATCHBACK 6 8
check R6 "hatchback 10 km -> 20 + 24 + 25 = 69" 200 '.fare.total == 69' true '.fare.distanceKm == 10' true
end_after SEDAN 6 8
check R6 "sedan 10 km -> 30 + 36 + 40 = 106 (different rates)" 200 '.fare.total == 106' true
end_after SEDAN 3 0
check R6 "sedan 3 km -> tiered 42, sedan minimum 80" 200 '.fare.total == 80' true
END_ID=$(jq -r .id <<<"$BODY")
req POST "/rides/$END_ID/end"
check R4 "ending a ride twice" 409 '.error.code' RIDE_NOT_ONGOING
req POST /rides/ride-999999/end
check R4 "ending an unknown ride" 404 '.error.code' RIDE_NOT_FOUND
next_region
D=$(driver HATCHBACK $REGION 0)
U=$(user)
book "$U" $REGION 0 HATCHBACK; R=$(jq -r .id <<<"$BODY")
req POST "/rides/$R/end"
move "$D" $REGION 0
check R4 "ending frees the driver" 200 '.status' AVAILABLE
book "$U" $REGION 0 HATCHBACK
check R4 "ending frees the user to book again" 201 '.driverId' "$D"
req GET "/rides/$R"
check R4 "ride can be fetched after completion" 200 '.status' COMPLETED '.dropoff != null' true
req GET /rides/ride-999999
check R4 "fetching an unknown ride" 404 '.error.code' RIDE_NOT_FOUND

section "R10 Coupons: add, delete, apply a valid coupon when starting a ride"
C="SAVE$(uniq)"
req POST /coupons "{\"code\": \"$C\", \"percentOff\": 10}"
check R10 "add a 10% coupon" 201 '.code' "$C"
req POST /coupons "{\"code\": \"$(tr 'A-Z' 'a-z' <<<"$C")\", \"percentOff\": 20}"
check R10 "duplicate code (case-insensitive)" 409 '.error.code' DUPLICATE_COUPON
req POST /coupons '{"code": "BAD", "percentOff": 0}'
check R10 "0% off is rejected" 400 '.error.code' VALIDATION_FAILED
req POST /coupons '{"code": "BAD", "percentOff": 150}'
check R10 "150% off is rejected" 400 '.error.code' VALIDATION_FAILED
req POST /coupons '{"code": "has space", "percentOff": 10}'
check R10 "invalid code format" 400 '.error.code' VALIDATION_FAILED
next_region
D=$(driver HATCHBACK $REGION 0)
book "$(user)" $REGION 0 HATCHBACK "$(tr 'A-Z' 'a-z' <<<"$C")"
check R10 "valid coupon accepted at booking (any case)" 201 '.couponCode' "$C"
R=$(jq -r .id <<<"$BODY")
move "$D" $(( REGION + 6 )) 8
req POST "/rides/$R/end"
check R10 "10% off a 69 fare -> 6.90 off, total 62.10" 200 '.fare.discount == 6.9' true '.fare.total == 62.1' true
CAP="CAP$(uniq)"
req POST /coupons "{\"code\": \"$CAP\", \"percentOff\": 50, \"maxDiscount\": 20}"
check R10 "add a capped coupon" 201 '.maxDiscount == 20' true
next_region
D=$(driver HATCHBACK $REGION 0)
book "$(user)" $REGION 0 HATCHBACK "$CAP"; R=$(jq -r .id <<<"$BODY")
move "$D" $(( REGION + 6 )) 8
req POST "/rides/$R/end"
check R10 "50% capped at 20 on 69 -> total 49" 200 '.fare.discount == 20' true '.fare.total == 49' true
next_region
D=$(driver HATCHBACK $REGION 0)
U=$(user)
book "$U" $REGION 0 HATCHBACK NOPE
check R10 "unknown coupon rejects the booking" 422 '.error.code' INVALID_COUPON
move "$D" $REGION 0
check R10 "...and the driver was not reserved" 200 '.status' AVAILABLE
GONE="GONE$(uniq)"
req POST /coupons "{\"code\": \"$GONE\", \"percentOff\": 50}"
book "$U" $REGION 0 HATCHBACK "$GONE"; KEPT=$(jq -r .id <<<"$BODY")
req DELETE "/coupons/$GONE"
check R10 "delete a coupon" 204
req DELETE "/coupons/$GONE"
check R10 "delete it again" 404 '.error.code' COUPON_NOT_FOUND
book "$(user)" $REGION 0 HATCHBACK "$GONE"
check R10 "deleted coupon can no longer be applied" 422 '.error.code' INVALID_COUPON
req POST "/rides/$KEPT/end"
check R10 "ride booked before deletion keeps its discount (50 -> 25)" 200 '.fare.total == 25' true

section "R7 + R8  Ride history (ongoing and completed) for a user and a driver"
next_region
D1=$(driver HATCHBACK $REGION 0)
U=$(user)
book "$U" $REGION 0 HATCHBACK; DONE=$(jq -r .id <<<"$BODY")
req POST "/rides/$DONE/end"
move "$D1" $(( REGION + 20 )) 0 >/dev/null
D2=$(driver HATCHBACK $REGION 0)
book "$U" $REGION 0 HATCHBACK; LIVE=$(jq -r .id <<<"$BODY")
req GET "/users/$U/rides"
check R7 "user history splits ongoing / completed" 200 '[.ongoing[].id] == ["'"$LIVE"'"]' true '[.completed[].id] == ["'"$DONE"'"]' true
req GET "/drivers/$D1/rides"
check R8 "driver history shows completed ride" 200 '[.completed[].id] == ["'"$DONE"'"]' true '.ongoing | length' 0
req GET "/drivers/$D2/rides"
check R8 "driver history shows ongoing ride" 200 '[.ongoing[].id] == ["'"$LIVE"'"]' true
req GET /users/user-999999/rides
check R7 "unknown user" 404 '.error.code' USER_NOT_FOUND
req GET /drivers/driver-999999/rides
check R8 "unknown driver" 404 '.error.code' DRIVER_NOT_FOUND

section "B3  Ride cancellation with a fee policy (default: free within 2 minutes)"
next_region
driver SEDAN $REGION 0 >/dev/null
U=$(user)
book "$U" $REGION 0 SEDAN; R=$(jq -r .id <<<"$BODY")
req POST "/rides/$R/cancel"
check B3 "cancel within grace period is free" 200 '.status' CANCELLED '.cancellationFee == 0' true
req POST "/rides/$R/cancel"
check B3 "cancel twice" 409 '.error.code' RIDE_NOT_ONGOING
req POST "/rides/$R/end"
check B3 "cannot end a cancelled ride" 409 '.error.code' RIDE_NOT_ONGOING
req GET "/users/$U/rides"
check B3 "cancelled ride appears in history" 200 '[.cancelled[].id] == ["'"$R"'"]' true
req POST /rides/ride-999999/cancel
check B3 "cancel unknown ride" 404 '.error.code' RIDE_NOT_FOUND

section "B1  Surge pricing (>= 75% of nearby cabs busy -> 1.25x)"
next_region
for _ in 1 2 3 4; do driver HATCHBACK $REGION 0 >/dev/null; done
for _ in 1 2 3; do book "$(user)" $REGION 0 HATCHBACK; done
book "$(user)" $REGION 0 HATCHBACK
check B1 "4th booking with 3/4 cabs busy locks in 1.25x" 201 '.surgeMultiplier == 1.25' true
R=$(jq -r .id <<<"$BODY"); D=$(jq -r .driverId <<<"$BODY")
move "$D" $(( REGION + 6 )) 8
req POST "/rides/$R/end"
check B1 "surged 10 km hatchback -> 69 * 1.25 = 86.25" 200 '.fare.total == 86.25' true

section "B4  Concurrency: many riders book the same single driver at once"
next_region
driver SEDAN $REGION 0 >/dev/null
RIDERS=()
for _ in $(seq 1 20); do RIDERS+=("$(user)"); done
CODES=$(printf '%s\n' "${RIDERS[@]}" | xargs -P 20 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST "$URL/rides" \
  -H 'Content-Type: application/json' --data-raw "{\"userId\": \"{}\", \"pickup\": {\"x\": $REGION, \"y\": 0}, \"carType\": \"SEDAN\"}")
BODY=$(jq -Rn '[inputs] | {created: map(select(. == "201")) | length, conflicts: map(select(. == "409")) | length}' <<<"$CODES")
STATUS=200
check B4 "20 parallel bookings -> exactly one 201, nineteen 409" 200 '.created' 1 '.conflicts' 19

section "API-wide behaviour"
req GET /no-such-route
check API "unknown route uses the same error shape" 404 '.error.code' NOT_FOUND
req DELETE /users
check API "unsupported method" 405 '.error.code' METHOD_NOT_ALLOWED
TMP=$(mktemp)
STATUS=$(curl -s -o "$TMP" -w '%{http_code}' -X POST "$URL/users" -H 'Content-Type: text/plain' -d 'x'); BODY=$(cat "$TMP"); rm -f "$TMP"
check API "unsupported content type" 415 '.error.code' UNSUPPORTED_MEDIA_TYPE
req GET /v3/api-docs
check API "OpenAPI document is served" 200 '.info.title' 'Ride Hailing Service'
STATUS=$(curl -s -L -o /dev/null -w '%{http_code}' "$URL/swagger-ui.html"); BODY='{}'
check API "Swagger UI is served" 200

if [[ -n $ALT ]]; then
  URL=$ALT
  section "Alternate config at $ALT (grace-period=0s, matching=highest-rated)"
  next_region
  driver HATCHBACK $REGION 0 >/dev/null
  book "$(user)" $REGION 0 HATCHBACK; R=$(jq -r .id <<<"$BODY")
  sleep 1
  req POST "/rides/$R/cancel"
  check B3 "hatchback cancelled after grace -> fee 25" 200 '.cancellationFee == 25' true
  next_region
  driver SEDAN $REGION 0 >/dev/null
  book "$(user)" $REGION 0 HATCHBACK; R=$(jq -r .id <<<"$BODY")
  sleep 1
  req POST "/rides/$R/cancel"
  check B3 "upgraded rider pays the hatchback fee (25, not 40)" 200 '.cancellationFee == 25' true
  next_region
  driver SEDAN $(( REGION + 1 )) 0 3.9 >/dev/null
  BEST=$(driver SEDAN $(( REGION + 4 )) 0 4.9)
  book "$(user)" $REGION 0 SEDAN
  check B2 "highest-rated strategy picks the 4.9 driver 4 km away over 3.9 at 1 km" 201 '.driverId' "$BEST"
fi

printf '\n\033[1m%d passed, %d failed\033[0m\n' "$PASS" "$FAIL"
(( FAIL == 0 ))
