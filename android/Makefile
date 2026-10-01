# Pocora — Android
#
# Every target runs one script from scripts/. Run `make` to list them.

ROLE ?= child
SERIAL ?=

.DEFAULT_GOAL := help

.PHONY: help
help:
	@echo "Setup"
	@echo "  make check         check this machine can build and run the project"
	@echo "  make image         install the system image for the virtual phones"
	@echo "  make phones        create and start the two virtual phones"
	@echo "  make phones-stop   shut the virtual phones down"
	@echo "Code"
	@echo "  make presets       write the presets JSON from ../preset/"
	@echo "  make format        format Kotlin, JSON and XML"
	@echo "  make test          run the unit tests of both apps"
	@echo "Build"
	@echo "  make build         debug APKs, child and parent"
	@echo "  make release       release APKs, minified"
	@echo "Run"
	@echo "  make run           build, install and open one app (ROLE=child or parent)"
	@echo "  make run-both      the same for both apps"
	@echo "                     add SERIAL=<serial> to use another device"
	@echo "  make pair          pair the two virtual phones, in place of scanning the code"
	@echo "Release"
	@echo "  make publish       bump the version, merge to main and tag it; GitHub builds the release"
	@echo "Clean"
	@echo "  make clean         remove build output and caches"

# --- Setup ---

.PHONY: check
check:
	@bash scripts/check.sh

.PHONY: image
image:
	@bash scripts/image.sh

.PHONY: phones
phones:
	@bash scripts/phones.sh start

.PHONY: phones-stop
phones-stop:
	@bash scripts/phones.sh stop

# --- Code ---

.PHONY: presets
presets:
	@bash scripts/presets.sh

.PHONY: format
format:
	@bash scripts/format.sh

.PHONY: test
test:
	@bash scripts/test.sh

# --- Build ---

.PHONY: build
build:
	@bash scripts/compile.sh debug

.PHONY: release
release:
	@bash scripts/compile.sh release

# --- Run ---

.PHONY: run
run:
	@bash scripts/run.sh $(ROLE) $(SERIAL)

.PHONY: run-both
run-both:
	@bash scripts/run.sh both $(SERIAL)

.PHONY: pair
pair:
	@bash scripts/pair.sh

# --- Release ---

# Interactive: it asks which version to release.
.PHONY: publish
publish:
	@bash scripts/publish.sh

# --- Clean ---

.PHONY: clean
clean:
	@bash scripts/clean.sh
