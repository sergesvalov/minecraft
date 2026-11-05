#!/bin/bash
# Uploads resourcepack/dist/HalloumiPack.zip to the GitHub release "resourcepack-v<N>"
# (N from resourcepack/dist/pack-version.txt), creating the release if needed.
# Idempotent: does nothing when the release already has the zip with the expected SHA1.
# Needs GH_TOKEN (Jenkins credential "git-access") with write access to the repo's releases.
# Uses only curl, grep and sed, so it runs on the Jenkins node without gh or jq.
set -euo pipefail

REPO="sergesvalov/minecraft"
cd "$(dirname "$0")/../dist"
VERSION=$(tr -d '[:space:]' < pack-version.txt)
TAG="resourcepack-v${VERSION}"
ASSET="HalloumiPack.zip"
URL="https://github.com/${REPO}/releases/download/${TAG}/${ASSET}"
SHA1=$(sha1sum "$ASSET" | cut -d' ' -f1)

# The compose file must point at exactly this zip, or clients reject the download
grep -q "RESOURCE_PACK: \"${URL}\"" ../../docker-compose.yml || { echo "docker-compose.yml RESOURCE_PACK is not ${URL}; run build-packs.mjs"; exit 1; }
grep -q "RESOURCE_PACK_SHA1: \"${SHA1}\"" ../../docker-compose.yml || { echo "docker-compose.yml RESOURCE_PACK_SHA1 is not ${SHA1}; run build-packs.mjs"; exit 1; }

# Already published? The release URL is public.
if curl -fsSL -o /tmp/halloumi-pack-check.zip "$URL" 2>/dev/null; then
    REMOTE=$(sha1sum /tmp/halloumi-pack-check.zip | cut -d' ' -f1)
    rm -f /tmp/halloumi-pack-check.zip
    if [ "$REMOTE" = "$SHA1" ]; then
        echo "Resource pack ${TAG} is already published (sha1 ${SHA1})"
        exit 0
    fi
    echo "Published ${TAG} has sha1 ${REMOTE}, expected ${SHA1}. Bump PACK_VERSION in build-packs.mjs instead of changing a released pack."
    exit 1
fi

: "${GH_TOKEN:?GH_TOKEN is required to publish the resource pack}"
API="https://api.github.com/repos/${REPO}"
AUTH=(-H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")

RELEASE=$(curl -fsS "${AUTH[@]}" "${API}/releases/tags/${TAG}" 2>/dev/null || true)
if [ -z "$RELEASE" ]; then
    echo "Creating release ${TAG}..."
    BODY=$(cat <<EOF
{"tag_name":"${TAG}","name":"Halloumi resource pack v${VERSION}","make_latest":"false",
 "body":"Optional resource pack with tavern dish icons (HalloumiTrader). The server offers it to Java players on join; Bedrock players get the .mcpack through Geyser.\\n\\nSHA1: ${SHA1}"}
EOF
)
    RELEASE=$(curl -fsS "${AUTH[@]}" -X POST "${API}/releases" -d "$BODY")
fi
RELEASE_ID=$(printf '%s' "$RELEASE" | grep -m1 -oE '"id": *[0-9]+' | grep -oE '[0-9]+')

echo "Uploading ${ASSET} to release ${TAG} (id ${RELEASE_ID})..."
curl -fsS "${AUTH[@]}" -H "Content-Type: application/zip" --data-binary "@${ASSET}" \
    "https://uploads.github.com/repos/${REPO}/releases/${RELEASE_ID}/assets?name=${ASSET}" > /dev/null

curl -fsSL -o /tmp/halloumi-pack-check.zip "$URL"
REMOTE=$(sha1sum /tmp/halloumi-pack-check.zip | cut -d' ' -f1)
rm -f /tmp/halloumi-pack-check.zip
[ "$REMOTE" = "$SHA1" ] || { echo "Uploaded file has sha1 ${REMOTE}, expected ${SHA1}"; exit 1; }
echo "Published ${URL} (sha1 ${SHA1})"
