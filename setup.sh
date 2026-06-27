#!/data/data/com.termux/files/usr/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# setup.sh  —  run this ONCE in Termux to push the project to GitHub
# After the push, GitHub Actions will build the APK automatically (~5 min).
# ─────────────────────────────────────────────────────────────────────────────
set -e

echo ""
echo "══════════════════════════════════════════"
echo "  Scientific Calculator  —  GitHub Setup  "
echo "══════════════════════════════════════════"
echo ""

# ── 1. Prerequisites ─────────────────────────────────────────────────────────
echo "[1/6] Installing prerequisites..."
pkg install -y git gh 2>/dev/null || true

# ── 2. GitHub login ──────────────────────────────────────────────────────────
echo ""
echo "[2/6] Logging into GitHub (browser will open or use token)..."
echo "      If prompted, select:  GitHub.com → HTTPS → Login with a web browser"
echo ""
gh auth status 2>/dev/null || {
    echo "    then re-run this script."
    exit 1
}

# ── 3. Git config ────────────────────────────────────────────────────────────
echo ""
echo "[3/6] Configuring git..."
git config --global user.email "$(gh api user --jq .email 2>/dev/null || echo 'user@example.com')"
git config --global user.name  "$(gh api user --jq .name  2>/dev/null || echo 'Calculator Dev')"

# ── 4. Init repo ─────────────────────────────────────────────────────────────
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo ""
echo "[4/6] Initialising git repository in: $PROJECT_DIR"

cd "$PROJECT_DIR"
git init -b main
git add .
git commit -m "Initial commit: Scientific Calculator Android app"

# ── 5. Create GitHub repo and push ───────────────────────────────────────────
REPO_NAME="scientific-calculator-android"
echo ""
echo "[5/6] Creating GitHub repository: $REPO_NAME ..."

gh repo create "$REPO_NAME" \
    --public \
    --description "Advanced scientific calculator for Android" \
    --source=. \
    --remote=origin \
    --push || {
        # Repo may already exist — just push
        git remote add origin "https://github.com/$(gh api user --jq .login)/$REPO_NAME.git" 2>/dev/null || true
        git push -u origin main --force
    }

GITHUB_USER=$(gh api user --jq .login)
REPO_URL="https://github.com/$GITHUB_USER/$REPO_NAME"

# ── 6. Done ───────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════"
echo "  ✓  Push complete!"
echo ""
echo "  GitHub Actions is now building your APK."
echo "  This takes about 4–6 minutes."
echo ""
echo "  Watch the build:"
echo "  $REPO_URL/actions"
echo ""
echo "  Download APK when ready:"
echo "  $REPO_URL/releases/latest"
echo ""
echo "  Tip: Open that releases link on your phone"
echo "  and tap the .apk file to install directly."
echo "══════════════════════════════════════════"
echo ""
