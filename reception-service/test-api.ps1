$baseUrl = "http://localhost:3001"
$receptionistId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

Write-Host "========================================="
Write-Host "  RECEPTION SERVICE END-TO-END API TEST  "
Write-Host "========================================="

$randomMobile = "+9471$(Get-Random -Minimum 1000000 -Maximum 9999999)"

Write-Host "`n1. Registering a New Patient..."
$patientJson = curl.exe -s -X POST "$baseUrl/patients" `
  -H "Content-Type: application/json" `
  -H "X-User-Id: $receptionistId" `
  -H "X-User-Role: receptionist" `
  -d "{`"firstName`":`"Amanda`", `"lastName`":`"Smith`", `"mobile`":`"$randomMobile`", `"dateOfBirth`":`"1992-05-15`", `"gender`":`"female`"}"

Write-Host $patientJson
$patientObj = $patientJson | ConvertFrom-Json
$patientId = $patientObj.data.id

Write-Host "`n2. Listing All Patients (Search)..."
curl.exe -s "$baseUrl/patients/search?q=Amanda" -H "X-User-Id: $receptionistId" -H "X-User-Role: receptionist"

Write-Host "`n`n3. Issuing OPD Token for patient: $patientId"
$tokenJson = curl.exe -s -X POST "$baseUrl/tokens" `
  -H "Content-Type: application/json" `
  -H "X-User-Id: $receptionistId" `
  -H "X-User-Role: receptionist" `
  -d "{`"patientId`":`"$patientId`", `"queueType`":`"opd`"}"

Write-Host $tokenJson
$tokenObj = $tokenJson | ConvertFrom-Json
$tokenId = $tokenObj.data.id

Write-Host "`n4. Fetching the OPD Live Queue..."
curl.exe -s "$baseUrl/queue/opd" -H "X-User-Id: $receptionistId" -H "X-User-Role: receptionist"

Write-Host "`n`n5. Updating Token Status to 'called' for token: $tokenId"
# This triggers the RabbitMQ publish event which WebSocket bridge listens to
curl.exe -s -X PUT "$baseUrl/tokens/$tokenId/status" `
  -H "Content-Type: application/json" `
  -H "X-User-Id: $receptionistId" `
  -H "X-User-Role: receptionist" `
  -d "{`"status`":`"called`"}"

Write-Host "`n`n6. Booking an Appointment for patient: $patientId"
$docId = "d2c3b4a5-e5f6-7890-abcd-ef1234567890"
curl.exe -s -X POST "$baseUrl/appointments" `
  -H "Content-Type: application/json" `
  -H "X-User-Id: $receptionistId" `
  -H "X-User-Role: receptionist" `
  -d "{`"patientId`":`"$patientId`", `"doctorId`":`"$docId`", `"appointmentDate`":`"2026-03-01`", `"fromTime`":`"09:00`", `"toTime`":`"09:15`"}"

Write-Host "`n`n7. Fetching Today's Appointments..."
curl.exe -s "$baseUrl/appointments" -H "X-User-Id: $receptionistId" -H "X-User-Role: receptionist"

Write-Host "`n`n========================================="
Write-Host "        ALL TESTS COMPLETED              "
Write-Host "========================================="
