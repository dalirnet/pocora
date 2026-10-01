#!/bin/bash
#
# Cut a release: bump the version in version.properties, commit it, merge to main and tag it.
# Pushing the tag is what starts release.yml, which builds both apps signed and minified and publishes
# pocora-parent-<version>.apk and pocora-child-<version>.apk as a GitHub Release.
#
# The apps show the same number: the build reads versionName from version.properties, and release.yml
# refuses a tag that does not match it. versionCode goes up by one with every release, since Android
# installs an update only over a lower one.
#
# Usage:  make publish    # interactive: bump the version, or rebuild the current one

. "$(dirname "$0")/common.sh"

# From the repo root: checking out main can remove android/ for a moment, when main does not have it yet,
# and a script standing inside it would lose its working directory.
cd "$(git rev-parse --show-toplevel)"

REMOTE="origin"
RELEASE_BRANCH="main"
VERSION_FILE="android/version.properties"

die() { fail "$@"; }
confirm() { read -rp "$1 [y/N]: " r; [[ "$r" == [yY] ]] || { echo "Aborted."; exit 0; }; }
confirm_text() { read -rp "$1: " r; [[ "$r" == "$2" ]] || die "input did not match '$2' — aborted"; }

read_property() { sed -n "s/^$1=//p" "$VERSION_FILE"; }

# Rewrite only the two values, so the release commit changes two lines and keeps the comments.
set_version() {
    local tmp
    tmp="$(mktemp)"
    sed -e "s/^versionName=.*/versionName=$1/" -e "s/^versionCode=.*/versionCode=$2/" "$VERSION_FILE" > "$tmp"
    mv "$tmp" "$VERSION_FILE"
}

# Preflight: the tools, the right directory, and nothing uncommitted to sweep into the bump.
command -v git >/dev/null 2>&1        || die "git is required"
[[ -f "$VERSION_FILE" ]]              || die "$VERSION_FILE not found"
[[ -z "$(git status --porcelain)" ]]  || die "working tree is not clean"

current="$(read_property versionName)"
code="$(read_property versionCode)"
[[ "$current" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "versionName is not X.Y.Z: ${current}"
[[ "$code" =~ ^[0-9]+$ ]]                    || die "versionCode is not a number: ${code}"

IFS='.' read -r major minor patch <<< "$current"
patch_bump="${major}.${minor}.$((patch + 1))"
minor_bump="${major}.$((minor + 1)).0"
major_bump="$((major + 1)).0.0"

echo
echo "Current version: ${current} (code ${code})"
echo "  0) rebuild → ${current}   (same version, fresh APKs from the latest code)"
echo "  1) patch   → ${patch_bump}"
echo "  2) minor   → ${minor_bump}"
echo "  3) major   → ${major_bump}"
read -rp "Choose [0-3]: " choice
case "$choice" in
    0) version="$current"; rebuild=1 ;;
    1) version="$patch_bump" ;;
    2) version="$minor_bump" ;;
    3) version="$major_bump" ;;
    *) echo "Cancelled."; exit 0 ;;
esac

tag="v${version}"
# A rebuild keeps its code: phones that installed it may reinstall it, but never take a lower one.
if [[ -n "${rebuild:-}" ]]; then new_code="$code"; else new_code="$((code + 1))"; fi

# Rebuilding an existing version replaces the release already published under that name, and moves its
# tag onto a different commit. Both are destructive, so this takes a typed answer rather than a y/N, and
# the version bump below becomes an empty commit.
if [[ -n "${rebuild:-}" ]]; then
    command -v gh >/dev/null 2>&1 || die "gh is required to replace the published release"
    echo
    echo "Rebuilding ${tag} will replace:"
    echo "  - the ${tag} GitHub Release and its APKs"
    echo "  - the ${tag} git tag, here and on ${REMOTE}"
    confirm_text "Type ${tag} to confirm the rebuild" "$tag"

    if gh release view "$tag" >/dev/null 2>&1; then
        gh release delete "$tag" --yes || die "could not delete the ${tag} release"
        echo "Deleted the ${tag} release"
    fi
    if git ls-remote --exit-code --tags "$REMOTE" "refs/tags/${tag}" >/dev/null 2>&1; then
        git push --delete "$REMOTE" "$tag" || die "could not delete ${tag} on ${REMOTE}"
        echo "Deleted ${tag} on ${REMOTE}"
    fi
    git tag -d "$tag" >/dev/null 2>&1 || true

    reconfirmed=1
    allow_empty=1
fi

git fetch --quiet "$REMOTE" "$RELEASE_BRANCH"
branch="$(git branch --show-current)"
[[ -n "$branch" ]] || die "not on a branch"

# Releasing straight from main means the bump has to land on top of what is already published.
if [[ "$branch" == "$RELEASE_BRANCH" ]]; then
    [[ "$(git rev-parse @)" == "$(git rev-parse "${REMOTE}/${RELEASE_BRANCH}")" ]] \
        || die "local ${RELEASE_BRANCH} is out of sync with ${REMOTE} — push or pull first"
fi

if [[ -z "${rebuild:-}" ]]; then
    git rev-parse -q --verify "refs/tags/${tag}" >/dev/null && die "tag ${tag} already exists"
    git ls-remote --exit-code --tags "$REMOTE" "refs/tags/${tag}" >/dev/null 2>&1 \
        && die "tag ${tag} already exists on ${REMOTE}"
fi

[[ -n "${reconfirmed:-}" ]] || confirm "Release ${tag} (code ${new_code}) from '${branch}'?"

# The version lands on the working branch first, so the branch you develop on carries the number it
# published, then the same commit is merged into main and tagged there.
set_version "$version" "$new_code"
git add "$VERSION_FILE"
git commit ${allow_empty:+--allow-empty} -m "chore: release ${tag}"
git push "$REMOTE" "$branch"

# Bring the release commit into main. The working branch is the source of truth, so -X theirs settles
# any drift in its favour rather than stopping the release on a conflict.
if [[ "$branch" != "$RELEASE_BRANCH" ]]; then
    [[ -n "${reconfirmed:-}" ]] || confirm "Merge '${branch}' into ${RELEASE_BRANCH} and tag the release there?"
    if git show-ref --verify --quiet "refs/heads/${RELEASE_BRANCH}"; then
        git checkout "$RELEASE_BRANCH"
        git merge --ff-only "${REMOTE}/${RELEASE_BRANCH}" || die "${RELEASE_BRANCH} diverged from ${REMOTE} — reconcile first"
    else
        git checkout -b "$RELEASE_BRANCH" "${REMOTE}/${RELEASE_BRANCH}"
    fi
    git merge --no-ff -X theirs "$branch" -m "Merge ${branch} into ${RELEASE_BRANCH} for ${tag}" \
        || die "merge failed — resolve conflicts and retry"
    git push "$REMOTE" "$RELEASE_BRANCH"
fi

# The tag is what starts release.yml, and the record of which commit was released.
git tag -a "$tag" -m "Release ${tag}"
git push "$REMOTE" "$tag"

# Leave the tree on the branch it started on rather than parked on main.
if [[ "$branch" != "$RELEASE_BRANCH" ]]; then
    git checkout "$branch"
fi

echo
echo "Pushed ${tag} — release.yml is building pocora-parent-${version}.apk and pocora-child-${version}.apk"
echo "Track it with: gh run list --workflow=release.yml"
