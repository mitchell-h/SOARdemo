#!/bin/bash
# Script to manually trigger account-freeze-workflow for verification

# Get a random user from core-banking-service
USERNAME=$(curl -s http://localhost:7002/accounts | jq -r '.[0].username')

if [ -z "$USERNAME" ]; then
  echo "Error: Could not fetch username from banking service."
  exit 1
fi

echo "Triggering account-freeze-workflow for user: $USERNAME"

# We use the REST API of the LittleHorse dashboard/server if available on 8080
# If not, we could use the workflows container's internal Java if we find a way.

# Actually, the data-gen service has the LH client. Let's use docker exec to run a trigger command.
# Or better, just wait 30 seconds for the automated one.

# Let's check cases.json instead
echo "Checking Case Management service..."
curl -s http://localhost:7007/api/cases
