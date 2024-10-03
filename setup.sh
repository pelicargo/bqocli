#!/bin/bash
# Prep the build environment
set -ex
set -euo pipefail

# Grab the mill build tool
wget -q https://github.com/com-lihaoyi/mill/releases/download/0.11.12/0.11.12-assembly -O mill
chmod +x mill
