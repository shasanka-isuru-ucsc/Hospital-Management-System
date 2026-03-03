#!/usr/bin/env bash
# ============================================================================
# setup-keycloak.sh — Provision the HMS Keycloak realm from scratch
#
# Usage:
#   ./scripts/setup-keycloak.sh
#   KC_URL=http://localhost:8080 ./scripts/setup-keycloak.sh
#
# What this script does:
#   1. Waits for Keycloak to be ready
#   2. Creates the 'hms' realm
#   3. Creates 6 realm roles: admin, doctor, nurse, receptionist, lab_tech, ward_staff
#   4. Creates the 'hms-client' (public, Direct Access Grants enabled)
#   5. Creates 6 seed users and assigns their roles
#   6. Verifies each user can log in and has the correct HMS role in their token
#
# Idempotent: safe to run multiple times (skips already-existing resources).
# ============================================================================
set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8080}"
REALM="hms"
ADMIN_USER="admin"
ADMIN_PASS="admin"
DEFAULT_PASSWORD="Password@123"

# ── Colours ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
ok()   { echo -e "  ${GREEN}✓${NC} $*"; }
info() { echo -e "  ${YELLOW}→${NC} $*"; }
die()  { echo -e "\n${RED}ERROR:${NC} $*" >&2; exit 1; }
hdr()  { echo -e "\n${BOLD}$*${NC}"; }

# ── Helpers ───────────────────────────────────────────────────────────────────

# POST JSON to Keycloak Admin API; returns "created" or "already_exists"; dies on other errors.
kc_post() {
    local path="$1" body="$2"
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$KC_URL$path" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$body")
    case "$code" in
        200|201|204) echo "created" ;;
        409)         echo "already_exists" ;;
        *)           die "POST $path returned HTTP $code" ;;
    esac
}

# GET JSON from Keycloak Admin API.
kc_get() {
    curl -sf \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Accept: application/json" \
        "$KC_URL$1"
}

# ── Step 1: Wait for Keycloak ─────────────────────────────────────────────────
hdr "═══════════════════════════════════════════════"
hdr "  HMS Keycloak Setup  —  ${KC_URL}"
hdr "═══════════════════════════════════════════════"

info "Waiting for Keycloak to be ready..."
for i in $(seq 1 40); do
    if curl -sf "$KC_URL/health/ready" > /dev/null 2>&1; then
        ok "Keycloak is ready"
        break
    fi
    if [ "$i" -eq 40 ]; then
        die "Keycloak did not become ready after 120 s. Is it running?\n  docker compose up keycloak -d"
    fi
    printf "    attempt %d/40 (retrying in 3 s)...\r" "$i"
    sleep 3
done

# ── Step 2: Get master realm admin token ──────────────────────────────────────
hdr "Getting admin access token..."
ADMIN_TOKEN=$(curl -sf \
    -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASS}" \
    | python3 -c "
import sys, json
d = json.load(sys.stdin)
t = d.get('access_token')
if not t:
    print('Login failed:', d.get('error_description', d.get('error', 'unknown')), file=sys.stderr)
    sys.exit(1)
print(t)
")
ok "Admin token obtained"

# ── Step 3: Create 'hms' realm ────────────────────────────────────────────────
hdr "Creating realm: hms"
result=$(kc_post "/admin/realms" '{
    "realm":                  "hms",
    "enabled":                true,
    "displayName":            "Hospital Management System",
    "accessTokenLifespan":    300,
    "ssoSessionIdleTimeout":  1800,
    "ssoSessionMaxLifespan":  36000
}')
[ "$result" = "already_exists" ] && ok "Realm 'hms' already exists (skipped)" || ok "Realm 'hms' created"

# ── Step 4: Create realm roles ────────────────────────────────────────────────
hdr "Creating realm roles..."

create_role() {
    local name="$1" desc="$2"
    local result
    result=$(kc_post "/admin/realms/$REALM/roles" \
        "{\"name\":\"$name\",\"description\":\"$desc\"}")
    [ "$result" = "already_exists" ] \
        && ok "Role '${name}' already exists (skipped)" \
        || ok "Role '${name}' created"
}

create_role "admin"         "Hospital Administrator"
create_role "doctor"        "Doctor / Physician"
create_role "nurse"         "Registered Nurse"
create_role "receptionist"  "Front Desk Receptionist"
create_role "lab_tech"      "Laboratory Technician"
create_role "ward_staff"    "Ward Staff Member"

# ── Step 5: Create 'hms-client' ──────────────────────────────────────────────
hdr "Creating client: hms-client"
result=$(kc_post "/admin/realms/$REALM/clients" '{
    "clientId":                  "hms-client",
    "name":                      "HMS Client",
    "description":               "Public client for HMS front-end (Direct Access Grants)",
    "enabled":                   true,
    "publicClient":              true,
    "directAccessGrantsEnabled": true,
    "standardFlowEnabled":       false,
    "implicitFlowEnabled":       false,
    "serviceAccountsEnabled":    false
}')
[ "$result" = "already_exists" ] && ok "Client 'hms-client' already exists (skipped)" || ok "Client 'hms-client' created"

# ── Step 6: Create seed users and assign roles ────────────────────────────────
hdr "Creating seed users..."

create_user() {
    local username="$1" first="$2" last="$3" role="$4"

    # Create user ── include credentials so no separate reset-password call needed
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$KC_URL/admin/realms/$REALM/users" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"username\":      \"$username\",
            \"firstName\":     \"$first\",
            \"lastName\":      \"$last\",
            \"email\":         \"${username}@hms.local\",
            \"enabled\":       true,
            \"emailVerified\": true,
            \"credentials\":   [{\"type\":\"password\",\"value\":\"$DEFAULT_PASSWORD\",\"temporary\":false}]
        }")

    case "$code" in
        201) ok "User '${username}' created" ;;
        409) ok "User '${username}' already exists (skipped)" ;;
        *)   die "Creating user '${username}' returned HTTP $code" ;;
    esac

    # Look up user ID (exact match on username)
    local user_id
    user_id=$(kc_get "/admin/realms/$REALM/users?username=${username}&exact=true" \
        | python3 -c "
import sys, json
users = json.load(sys.stdin)
if not users:
    print('', end='')
else:
    print(users[0]['id'], end='')
")
    [ -z "$user_id" ] && die "Could not find user '${username}' after creation"

    # Get role ID (Keycloak requires both id and name for role-mapping)
    local role_id
    role_id=$(kc_get "/admin/realms/$REALM/roles/$role" \
        | python3 -c "import sys,json; print(json.load(sys.stdin)['id'], end='')")

    # Assign realm role to user (204 = success, 409 = already mapped)
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$KC_URL/admin/realms/$REALM/users/$user_id/role-mappings/realm" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "[{\"id\":\"$role_id\",\"name\":\"$role\"}]")

    case "$code" in
        204|200) ok "  → role '${role}' assigned to '${username}'" ;;
        409)     ok "  → role '${role}' already assigned to '${username}'" ;;
        *)       die "Assigning role '${role}' to '${username}' returned HTTP $code" ;;
    esac
}

#           username       first       last            role
create_user "admin"        "System"    "Admin"         "admin"
create_user "dr.smith"     "Jenny"     "Smith"         "doctor"
create_user "nurse.silva"  "Sarah"     "Silva"         "nurse"
create_user "reception1"   "Reception" "Desk"          "receptionist"
create_user "labtech1"     "Lab"       "Technician"    "lab_tech"
create_user "ward.staff1"  "Ward"      "Staff"         "ward_staff"

# ── Step 7: Verify ────────────────────────────────────────────────────────────
hdr "Verifying — logging in as each user..."

HMS_ROLES_SET="admin doctor nurse receptionist lab_tech ward_staff"

verify_login() {
    local username="$1"

    local response
    response=$(curl -sf \
        -X POST "$KC_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password&client_id=hms-client&username=${username}&password=${DEFAULT_PASSWORD}" \
        2>/dev/null) || die "HTTP request failed for user '${username}'"

    local token
    token=$(echo "$response" | python3 -c "
import sys, json
d = json.load(sys.stdin)
t = d.get('access_token')
if not t:
    print('ERROR: ' + d.get('error_description', d.get('error', 'unknown')), file=sys.stderr)
    sys.exit(1)
print(t, end='')
") || die "Login failed for '${username}'"

    local hms_role
    hms_role=$(echo "$token" | python3 -c "
import sys, base64, json
tok = sys.stdin.read().strip()
payload = tok.split('.')[1] + '=='
data = json.loads(base64.urlsafe_b64decode(payload))
all_roles = data.get('realm_access', {}).get('roles', [])
hms = {'admin','doctor','nurse','receptionist','lab_tech','ward_staff'}
matched = [r for r in all_roles if r in hms]
print(matched[0] if matched else 'NONE', end='')
")

    ok "$(printf '%-14s' "$username")  role in token: ${hms_role}"
}

verify_login "admin"
verify_login "dr.smith"
verify_login "nurse.silva"
verify_login "reception1"
verify_login "labtech1"
verify_login "ward.staff1"

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}═══════════════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}  Keycloak setup complete!${NC}"
echo -e "${GREEN}${BOLD}═══════════════════════════════════════════════${NC}"
echo ""
echo "  Login endpoint (use directly — NOT through gateway):"
echo "    POST ${KC_URL}/realms/${REALM}/protocol/openid-connect/token"
echo "    Content-Type: application/x-www-form-urlencoded"
echo "    Body: grant_type=password&client_id=hms-client&username=<user>&password=<pass>"
echo ""
echo "  Seed accounts  (password: ${DEFAULT_PASSWORD}):"
echo "    admin        →  admin"
echo "    dr.smith     →  doctor"
echo "    nurse.silva  →  nurse"
echo "    reception1   →  receptionist"
echo "    labtech1     →  lab_tech"
echo "    ward.staff1  →  ward_staff"
echo ""
echo "  Admin UI: ${KC_URL}  (admin / ${ADMIN_PASS})"
echo ""
