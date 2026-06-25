#!/bin/bash
# Script untuk push project ke GitHub
# Usage: ./push-to-github.sh <github-username> <repo-name>

if [ $# -lt 2 ]; then
    echo "Usage: $0 <github-username> <repo-name>"
    echo "Example: $0 soe1hom-arch AFFT-Android"
    exit 1
fi

USERNAME=$1
REPO=$2

echo "==> Membuat repo di GitHub dulu ya: https://github.com/new"
echo "    Nama repo: $REPO"
echo "    Jangan centang apapun (README, .gitignore, license)"
echo ""
read -p "Sudah buat repo? Press Enter to continue..."

echo "==> Push ke GitHub..."
git remote add origin "https://github.com/$USERNAME/$REPO.git"
git branch -m main
git push -u origin main

echo ""
echo "==> Selesai! APK akan otomatis dibuild oleh GitHub Actions."
echo "    Cek di: https://github.com/$USERNAME/$REPO/actions"
echo "    Download APK dari: https://github.com/$USERNAME/$REPO/actions/workflows/build-apk.yml"
