#!/usr/bin/env sh

# knowhub NAS 部署脚本：按不可变镜像标签更新三项应用，并在失败时恢复上一版本。

set -eu

DEPLOY_DIR=${DEPLOY_DIR:?DEPLOY_DIR is required}
IMAGE_PREFIX=${IMAGE_PREFIX:?IMAGE_PREFIX is required}
IMAGE_TAG=${IMAGE_TAG:?IMAGE_TAG is required}

cd "$DEPLOY_DIR"

# UGREEN Docker may generate docker-compose.yaml, while other environments
# use docker-compose.yml. Keep both names supported without changing the
# compose file managed by the NAS application.
if [ -f docker-compose.yml ]; then
    COMPOSE_FILE=docker-compose.yml
elif [ -f docker-compose.yaml ]; then
    COMPOSE_FILE=docker-compose.yaml
else
    echo "Neither docker-compose.yml nor docker-compose.yaml was found in $DEPLOY_DIR" >&2
    exit 1
fi

if [ ! -f .env ]; then
    echo "Missing .env in $DEPLOY_DIR" >&2
    exit 1
fi

echo "Using Compose file: $COMPOSE_FILE"

ENV_BACKUP=$(mktemp .knowhub-env-backup.XXXXXX)
chmod 600 "$ENV_BACKUP"
cp .env "$ENV_BACKUP"
trap 'rm -f "$ENV_BACKUP"' EXIT

previous_tag=$(awk -F= '$1 == "KNOWHUB_IMAGE_TAG" { print substr($0, index($0, "=") + 1); exit }' .env)
previous_tag=${previous_tag:-latest}

set_env_value() {
    key=$1
    value=$2
    temp_file=$(mktemp .knowhub-env.XXXXXX)
    awk -v key="$key" -v value="$value" '
        BEGIN { replaced = 0 }
        index($0, key "=") == 1 { print key "=" value; replaced = 1; next }
        { print }
        END { if (!replaced) print key "=" value }
    ' .env > "$temp_file"
    mv "$temp_file" .env
}

rollback_available=no
for service in backend portal admin; do
    old_image="$IMAGE_PREFIX/$service:$previous_tag"
    rollback_image="$IMAGE_PREFIX/$service:rollback"
    if docker image inspect "$old_image" >/dev/null 2>&1; then
        docker tag "$old_image" "$rollback_image"
        rollback_available=yes
    fi
done

set_env_value KNOWHUB_IMAGE_TAG "$IMAGE_TAG"
KNOWHUB_IMAGE_TAG="$IMAGE_TAG"
export KNOWHUB_IMAGE_TAG="$IMAGE_TAG"

check_url() {
    url=$1
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --max-time 10 "$url" >/dev/null
    elif command -v wget >/dev/null 2>&1; then
        wget -q --timeout=10 -O /dev/null "$url"
    else
        echo "Neither curl nor wget is installed; cannot run HTTP health checks." >&2
        return 1
    fi
}

wait_for_url() {
    url=$1
    attempts=30
    while [ "$attempts" -gt 0 ]; do
        if check_url "$url"; then
            return 0
        fi
        attempts=$((attempts - 1))
        sleep 5
    done
    return 1
}

restore_previous() {
    echo "Deployment health check failed; restoring the previous release." >&2
    cp "$ENV_BACKUP" .env
    if [ "$rollback_available" = yes ]; then
        export KNOWHUB_IMAGE_TAG=rollback
        docker compose -f "$COMPOSE_FILE" up -d --no-build backend portal admin || true
    else
        echo "No previous application images were available for automatic rollback." >&2
    fi
}

if ! docker compose -f "$COMPOSE_FILE" pull backend portal admin; then
    restore_previous
    exit 1
fi

if ! docker compose -f "$COMPOSE_FILE" up -d --no-build backend portal admin; then
    restore_previous
    exit 1
fi

if ! wait_for_url "http://127.0.0.1:5070/api/portal/blog/stats" \
    || ! wait_for_url "http://127.0.0.1:5070/" \
    || ! wait_for_url "http://127.0.0.1:5077/"; then
    restore_previous
    exit 1
fi

docker compose -f "$COMPOSE_FILE" ps backend portal admin
echo "knowhub deployment succeeded: $IMAGE_TAG"
